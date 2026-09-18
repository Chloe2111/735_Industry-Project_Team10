package com.vcnity.backend.community.service;

import com.vcnity.backend.community.model.CommunityGroup;
import com.vcnity.backend.community.repository.CommunityGroupRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CommunityGroupService {

    private final CommunityGroupRepository groupRepository;

    public CommunityGroupService(CommunityGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    // Create a new community group
    public CommunityGroup createGroup(CommunityGroup group) {
        return groupRepository.save(group);
    }

    // Get all community groups
    public List<CommunityGroup> getAllGroups() {
        return groupRepository.findAll();
    }

    // Get a community group by ID
    public Optional<CommunityGroup> getGroupById(String id) {
        return groupRepository.findById(id);
    }

    // Update an existing community group
    public Optional<CommunityGroup> updateGroup(
            String id,
            CommunityGroup updatedGroup) {

        return groupRepository.findById(id).map(existingGroup -> {
            existingGroup.setName(updatedGroup.getName());
            existingGroup.setDescription(updatedGroup.getDescription());
            existingGroup.setCategory(updatedGroup.getCategory());

            return groupRepository.save(existingGroup);
        });
    }

    // Delete a community group
    public boolean deleteGroup(String id) {

        if (!groupRepository.existsById(id)) {
            return false;
        }

        groupRepository.deleteById(id);
        return true;
    }
}